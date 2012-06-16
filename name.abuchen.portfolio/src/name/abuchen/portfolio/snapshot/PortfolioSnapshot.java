package name.abuchen.portfolio.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityPrice;
import name.abuchen.portfolio.model.Values;

public class PortfolioSnapshot
{
    // //////////////////////////////////////////////////////////////
    // factory methods
    // //////////////////////////////////////////////////////////////

    public static PortfolioSnapshot create(Portfolio portfolio, Date time)
    {
        Map<Security, SecurityPosition> positions = new HashMap<Security, SecurityPosition>();

        for (PortfolioTransaction t : portfolio.getTransactions())
        {
            if (t.getDate().getTime() <= time.getTime())
            {
                switch (t.getType())
                {
                    case TRANSFER_IN:
                    case BUY:
                    {
                        SecurityPosition p = positions.get(t.getSecurity());
                        if (p == null)
                            positions.put(t.getSecurity(), p = new SecurityPosition(t.getSecurity()));
                        p.addTransaction(t);
                        break;
                    }
                    case TRANSFER_OUT:
                    case SELL:
                    {
                        SecurityPosition p = positions.get(t.getSecurity());
                        if (p == null)
                            positions.put(t.getSecurity(), p = new SecurityPosition(t.getSecurity()));
                        p.addTransaction(t);
                        break;
                    }
                    default:
                        throw new UnsupportedOperationException("Unsupported operation: " + t.getType()); //$NON-NLS-1$
                }
            }
        }

        ArrayList<SecurityPosition> collection = new ArrayList<SecurityPosition>(positions.values());
        for (Iterator<SecurityPosition> iter = collection.iterator(); iter.hasNext();)
        {
            SecurityPosition p = iter.next();

            if (p.getShares() == 0)
            {
                iter.remove();
            }
            else
            {
                SecurityPrice price = p.getSecurity().getSecurityPrice(time);
                p.setPrice(price);
            }
        }

        return new PortfolioSnapshot(portfolio, time, collection);
    }

    public static PortfolioSnapshot merge(List<PortfolioSnapshot> snapshots)
    {
        if (snapshots.isEmpty())
            throw new RuntimeException("Error: PortfolioSnapshots to be merged must not be empty"); //$NON-NLS-1$

        Portfolio portfolio = new Portfolio();
        portfolio.setName(Messages.LabelJointPortfolio);

        Map<Security, SecurityPosition> securities = new HashMap<Security, SecurityPosition>();
        for (PortfolioSnapshot s : snapshots)
        {
            portfolio.addAllTransaction(s.getSource().getTransactions());
            for (SecurityPosition p : s.getPositions())
            {
                SecurityPosition pos = securities.get(p.getSecurity());
                if (pos == null)
                    securities.put(p.getSecurity(), p);
                else
                    securities.put(p.getSecurity(),
                                    new SecurityPosition(pos.getSecurity(), pos.getPrice(), pos.getShares()
                                                    + p.getShares()));
            }
        }

        return new PortfolioSnapshot(portfolio, snapshots.get(0).getTime(), new ArrayList<SecurityPosition>(
                        securities.values()));
    }

    // //////////////////////////////////////////////////////////////
    // instance impl
    // //////////////////////////////////////////////////////////////

    private Portfolio portfolio;
    private Date time;

    private List<SecurityPosition> positions = new ArrayList<SecurityPosition>();

    private PortfolioSnapshot(Portfolio source, Date time, List<SecurityPosition> positions)
    {
        this.portfolio = source;
        this.time = time;
        this.positions = positions;
    }

    public Portfolio getSource()
    {
        return portfolio;
    }

    public Date getTime()
    {
        return time;
    }

    public List<SecurityPosition> getPositions()
    {
        return positions;
    }

    public Map<Security, SecurityPosition> getPositionsBySecurity()
    {
        Map<Security, SecurityPosition> map = new HashMap<Security, SecurityPosition>();
        for (SecurityPosition p : positions)
            map.put(p.getSecurity(), p);
        return map;
    }

    public long getValue()
    {
        long value = 0;
        for (SecurityPosition p : positions)
            value += p.calculateValue();

        return value;
    }

    private static class TotalsCategory extends AssetCategory
    {
        public TotalsCategory(long valuation)
        {
            super(null, valuation);
            this.setValuation(valuation);
        }
    }

    public List<AssetCategory> groupByCategory()
    {
        TotalsCategory totals = new TotalsCategory(this.getValue());

        List<AssetCategory> categories = new ArrayList<AssetCategory>();
        Map<Security.AssetClass, AssetCategory> class2category = new HashMap<Security.AssetClass, AssetCategory>();

        for (Security.AssetClass ac : Security.AssetClass.values())
        {
            AssetCategory category = new AssetCategory(ac, totals.getValuation());
            categories.add(category);
            class2category.put(ac, category);
        }

        // total line
        categories.add(totals);

        for (SecurityPosition pos : this.getPositions())
        {
            AssetCategory cat = class2category.get(pos.getSecurity().getType());
            cat.addPosition(new AssetPosition(pos, totals.getValuation()));
        }

        for (AssetCategory cat : categories)
            Collections.sort(cat.getPositions());

        return categories;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString()
    {
        StringBuilder buf = new StringBuilder();
        buf.append("-----------------------------------------------------\n");
        buf.append(portfolio.getName()).append("\n");
        buf.append(String.format("Date: %tF\n", time));
        buf.append("-----------------------------------------------------\n");

        for (SecurityPosition p : positions)
            buf.append(String.format("%5d %-25s %,10.2f %,10.2f\n", //
                            p.getShares(), //
                            p.getSecurity().getName(), //
                            p.getPrice().getValue() / Values.Quote.divider(), //
                            p.calculateValue() / Values.Amount.divider()));

        buf.append("-----------------------------------------------------\n");

        return buf.toString();
    }
}
